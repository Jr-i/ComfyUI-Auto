package pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

// 使用Record简化POJO类，自动获得构造函数、getter、equals, hashCode, toString
public record ModelConfig(String name, Parameters parameters) {
    public record Parameters(
        int steps,
        double cfg,
        @JsonProperty("sampler_name") String samplerName,
        String scheduler,
        String positive,
        String negative,
        int width,
        int height,
        boolean steps_test,
        boolean cfg_test
    ) {}
}
