package org.ikigaidigital.adapter.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the hand-written OpenAPI contract for Swagger UI, which is
 * configured to load /openapi.yaml. Replaces a classpath-root resource
 * handler, which Spring Framework 6.2 rejects as an unsafe location.
 */
@RestController
public class OpenApiContractController {

    @GetMapping(value = "/openapi.yaml", produces = MediaType.APPLICATION_YAML_VALUE)
    public Resource openApiContract() {
        return new ClassPathResource("openapi.yaml");
    }
}
