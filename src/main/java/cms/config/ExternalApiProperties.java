package cms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "external-api.security")
@Getter
@Setter
public class ExternalApiProperties {
    private String apiKey;
    private List<String> whitelistIps;
}