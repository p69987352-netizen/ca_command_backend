package com.caCommand.caCommand.services.pipeline.parsers;

import com.caCommand.caCommand.enums.DocumentType;
import com.caCommand.caCommand.services.pipeline.parsers.models.DocumentContext;
import com.caCommand.caCommand.services.pipeline.parsers.models.ExtractionResult;

public interface DocumentParser {
    boolean supports(DocumentType type);
    ExtractionResult parse(DocumentContext context);
}
