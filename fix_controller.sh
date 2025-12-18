#!/bin/bash

# Fix AiAssistantController.java
FILE="/data/Projet_fwk/scan/core/ws-rest/src/main/java/tn/rnu/eniso/fwk/scan/core/ws/rest/AiAssistantController.java"

# Replace Lombok imports with SLF4J imports
sed -i 's/import lombok.extern.slf4j.Slf4j;/import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;/' "$FILE"
sed -i 's/import lombok.RequiredArgsConstructor;//' "$FILE"

# Remove @Slf4j and @RequiredArgsConstructor annotations
sed -i '/@Slf4j/d' "$FILE"
sed -i '/@RequiredArgsConstructor/d' "$FILE"

# Add Logger field after class declaration
sed -i '/^public class AiAssistantController/a\    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);' "$FILE"

# Add constructor after suricataService field
sed -i '/private final SuricataService suricataService;/a\    public AiAssistantController(AiAssistantService aiAssistantService, SuricataService suricataService) {\n        this.aiAssistantService = aiAssistantService;\n        this.suricataService = suricataService;\n    }' "$FILE"

echo "Fixed AiAssistantController.java"
