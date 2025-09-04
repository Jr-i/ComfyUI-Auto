package pojo;

import lombok.Getter;
import lombok.Setter;

// getter/setter是必需的，以便Jackson可以序列化/反序列化
@Getter
@Setter
public class GenerationState {
    private int currentModelIndex;
    private int currentPromptIndex;
    private int currentRepetitionCount;
}
