package oldVersion;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Remark {
    // 当前加载的配置序号
    private int configIndex;
    // 图片是否为横屏
    private boolean isLandscape;
    // char.txt的特征码
    private String charFeature;
    // location.txt的特征码
    private String locationFeature;
    // char.txt的总行数
    private int charTotalLines;
    // location.txt的总行数
    private int locationTotalLines;
    // 当前char.txt的行数
    private int charIndex;
    // 当前location.txt的行数
    private int locationIndex;

    public void incrementCharIndex() {
        this.charIndex++;
    }

    public void incrementLocationIndex() {
        this.locationIndex++;
    }

    public void incrementConfigIndex() {
        if (this.configIndex >= DynamicPrompt.configName.length - 1) {
            this.configIndex = 0;
        } else {
            this.configIndex++;
        }
    }
}