package com.homihq.db2rest.rest.read.processor;

import com.homihq.db2rest.rest.read.dto.ReadContext;

public interface ReadProcessor {

    void process(ReadContext readContext);
}
