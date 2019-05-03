package chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import io.jaegertracing.Configuration;
import io.jaegertracing.Configuration.ReporterConfiguration;
import io.jaegertracing.Configuration.SamplerConfiguration;
import lib.AppId;

@SpringBootApplication
@ComponentScan(basePackages = {"lib", "chat"})
public class App {
    @Bean
    public AppId appId() {
        return new AppId("chat-api");
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
    @Bean
    public io.opentracing.Tracer initTracer() {
        SamplerConfiguration samplerConfig = new SamplerConfiguration().withType("const").withParam(1);
        ReporterConfiguration reporterConfig = new ReporterConfiguration().withLogSpans(true);
        return new Configuration("chat-api-1").withSampler(samplerConfig).withReporter(reporterConfig).getTracer();
    }
}