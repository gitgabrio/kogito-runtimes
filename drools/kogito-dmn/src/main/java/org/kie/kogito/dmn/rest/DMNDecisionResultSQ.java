/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.dmn.rest;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.core.internal.utils.MarshallingStubUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DMNDecisionResultSQ implements Serializable, DMNDecisionResult {

    private String decisionId;

    private String decisionName;

    private Object result;

    private List<DMNMessageSQ> messages = new ArrayList<>();

    private DecisionEvaluationStatus status;

    public DMNDecisionResultSQ() {
        // Intentionally blank.
    }

    public static DMNDecisionResultSQ of(DMNDecisionResult value) {
        DMNDecisionResultSQ res = new DMNDecisionResultSQ();
        res.decisionId = value.getDecisionId();
        res.decisionName = value.getDecisionName();
        res.setResult(value.getResult());
        res.setMessages(value.getMessages());
        res.status = value.getEvaluationStatus();
        return res;
    }

    @JsonProperty("decision-id")
    @Override
    public String getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    @JsonProperty("decision-name")
    @Override
    public String getDecisionName() {
        return decisionName;
    }

    public void setDecisionName(String decisionName) {
        this.decisionName = decisionName;
    }

    @JsonProperty("status")
    @Override
    public DecisionEvaluationStatus getEvaluationStatus() {
        return status;
    }

    public void setEvaluationStatus(DecisionEvaluationStatus status) {
        this.status = status;
    }

    @JsonProperty("result")
    @Override
    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = MarshallingStubUtils.stubDMNResult(result, String::valueOf);
    }

    @JsonProperty("messages")
    public List<DMNMessage> getMessages() {
        return (List) messages;
    }

    public void setMessages(List<DMNMessage> messages) {
        for (DMNMessage m : messages) {
            this.messages.add(DMNMessageSQ.of(m));
        }
    }

    @Override
    public boolean hasErrors() {
        return messages != null && messages.stream().anyMatch(m -> m.getSeverity() == DMNMessage.Severity.ERROR);
    }
}