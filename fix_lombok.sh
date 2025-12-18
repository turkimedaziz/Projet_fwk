#!/bin/bash

# Fix AiAssistantServiceImpl.java
FILE="/data/Projet_fwk/scan/core/service-impl/src/main/java/tn/rnu/eniso/fwk/scan/core/service/impl/AiAssistantServiceImpl.java"

# Replace Lombok import with SLF4J imports
sed -i 's/import lombok.extern.slf4j.Slf4j;/import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;/' "$FILE"
sed -i 's/import lombok.RequiredArgsConstructor;//' "$FILE"

# Remove @Slf4j and @RequiredArgsConstructor annotations
sed -i '/@Slf4j/d' "$FILE"
sed -i '/@RequiredArgsConstructor/d' "$FILE"

# Add Logger field after class declaration
sed -i '/^public class AiAssistantServiceImpl/a\    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceImpl.class);' "$FILE"

# Add constructor after alertRepository field
sed -i '/private final AlertRepository alertRepository;/a\    public AiAssistantServiceImpl(HuggingFaceClient huggingFaceClient, AlertRepository alertRepository) {\n        this.huggingFaceClient = huggingFaceClient;\n        this.alertRepository = alertRepository;\n    }' "$FILE"

echo "Fixed AiAssistantServiceImpl.java"
