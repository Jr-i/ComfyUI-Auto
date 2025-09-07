import pojo.GenerationState;
import pojo.ModelConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WorkflowGenerator {

    public enum TraversalStrategy {
        PROMPT_FIRST,
        MODEL_FIRST
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final SecureRandom secureRandom = new SecureRandom();

    // Configuration
    private final TraversalStrategy strategy;
    private final int repetitionCount;
    private final File stateFile;

    // Data
    private final ObjectNode workflowTemplate;
    private final List<ModelConfig> models;
    private final List<String> prompts;

    public WorkflowGenerator() {
        // 1. 加载主配置
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.strategy = TraversalStrategy.valueOf(props.getProperty("traversal.strategy", "PROMPT_FIRST").toUpperCase());
        this.repetitionCount = Integer.parseInt(props.getProperty("repetition.count", "1"));

        // 遍历状态
        this.stateFile = new File(props.getProperty("path.state"));

        try {
            this.workflowTemplate = (ObjectNode) objectMapper.readTree(
                    new File(props.getProperty("path.template")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            // todo 校验文件是否被更改，如被更改应该重新开始循环
            this.models = objectMapper.readValue((
                    new File(props.getProperty("path.models"))), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //提示词列表
        this.prompts = deleteEmptyLine(props.getProperty("path.prompts"));
    }

    private List<String> deleteEmptyLine(String filePath) {
        try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
            return lines.filter(line -> !line.trim().isEmpty()).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized GenerationState readState() {
        if (stateFile.exists() && stateFile.length() > 0) {
            try {
                return objectMapper.readValue(stateFile, GenerationState.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // 如果文件不存在或为空，返回初始状态
        return new GenerationState();
    }

    private synchronized void writeState(GenerationState state) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 生成下一个 ComfyUI 工作流 ObjectNode
     * 该方法是线程安全的
     *
     * @return 配置好的 ObjectNode
     * @throws IOException 如果读写文件失败
     */
    public synchronized ObjectNode nextWorkflow() {
        // 1. 读取当前状态
        GenerationState state = readState();

        // 2. 获取当前要使用的模型和提示词
        ModelConfig currentModel = models.get(state.getCurrentModelIndex());
        String currentPrompt = prompts.get(state.getCurrentPromptIndex());

        // 3. 基于模板创建并填充工作流
        ObjectNode workflow = workflowTemplate.deepCopy();

        // 3.3 更新采样器参数和新的seed (Node "3")
        ObjectNode KSamplerNode = workflow.with("3").with("inputs");
        ModelConfig.Parameters params = currentModel.parameters();
        KSamplerNode.put("seed", new BigInteger(64, secureRandom));
        KSamplerNode.put("steps", params.steps());
        KSamplerNode.put("cfg", params.cfg());
        KSamplerNode.put("sampler_name", params.samplerName());
        KSamplerNode.put("scheduler", params.scheduler());

        // cfg 和 step 随机浮动
        int count = state.getCurrentRepetitionCount();
        if (params.cfg_test()) {
            KSamplerNode.put("cfg", count % 3 - 1 + params.cfg());
        }
        if (params.steps_test()) {
            KSamplerNode.put("steps", count / 3 - 2 + params.steps());
        }

        // 横屏还是竖屏
        if (count < 15) {
            workflow.with("5").with("inputs").put("width", params.width());
            workflow.with("5").with("inputs").put("height", params.height());
        } else {
            workflow.with("5").with("inputs").put("width", params.height());
            workflow.with("5").with("inputs").put("height", params.width());
        }

        String checkpointName = currentModel.name();
        workflow.with("4").with("inputs").put("ckpt_name", checkpointName);

        // 修改生成文件名称
        workflow.with("14").with("inputs").put("filename_prefix", params.filenamePrefix());

        workflow.with("6").with("inputs").put("text", params.positive() + currentPrompt);
        workflow.with("7").with("inputs").put("text", params.negative());

        // 4. 计算并保存下一个状态
        updateState(state);
        writeState(state);

        // 5. 返回生成的工作流
        return workflow;
    }

    private void updateState(GenerationState state) {
        state.setCurrentRepetitionCount(state.getCurrentRepetitionCount() + 1);

        if (state.getCurrentRepetitionCount() < repetitionCount) {
            // 当前组合还没重复够次数，直接返回
            return;
        }

        // 重置重复计数器，准备移动到下一个组合
        state.setCurrentRepetitionCount(0);

        if (strategy == TraversalStrategy.PROMPT_FIRST) {
            // 先遍历提示词
            int nextPromptIndex = state.getCurrentPromptIndex() + 1;
            if (nextPromptIndex >= prompts.size()) {
                // 提示词已遍历完，移动到下一个模型，并重置提示词索引
                state.setCurrentPromptIndex(0);
                int nextModelIndex = state.getCurrentModelIndex() + 1;
                if (nextModelIndex >= models.size()) {
                    // 所有模型已遍历完，从头开始
                    state.setCurrentModelIndex(0);
                } else {
                    state.setCurrentModelIndex(nextModelIndex);
                }
            } else {
                state.setCurrentPromptIndex(nextPromptIndex);
            }
        } else { // MODEL_FIRST
            // 先遍历模型
            int nextModelIndex = state.getCurrentModelIndex() + 1;
            if (nextModelIndex >= models.size()) {
                // 模型已遍历完，移动到下一个提示词，并重置模型索引
                state.setCurrentModelIndex(0);
                int nextPromptIndex = state.getCurrentPromptIndex() + 1;
                if (nextPromptIndex >= prompts.size()) {
                    // 所有提示词已遍历完，从头开始
                    state.setCurrentPromptIndex(0);
                } else {
                    state.setCurrentPromptIndex(nextPromptIndex);
                }
            } else {
                state.setCurrentModelIndex(nextModelIndex);
            }
        }
    }
}
