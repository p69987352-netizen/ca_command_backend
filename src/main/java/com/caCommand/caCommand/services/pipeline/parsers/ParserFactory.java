package com.caCommand.caCommand.services.pipeline.parsers;

import com.caCommand.caCommand.enums.DocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ParserFactory {

    private final List<DocumentParser> parsers;

    @Autowired
    public ParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public Optional<DocumentParser> getParserForType(DocumentType type) {
        return parsers.stream()
                .filter(parser -> parser.supports(type))
                .findFirst();
    }
}
