package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.posts")
public class PostProperties {

    /** Maximum number of links allowed in a title + description. */
    private int maxLinks = 5;

    /**
     * An identical title + description by the same author inside this window is
     * rejected with 409. Zero disables the duplicate guard.
     */
    private int duplicateWindowHours = 24;
}
