package com.stockops.ai.bedrock.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockops.ai.bedrock.BedrockConverseOrchestrator;
import com.stockops.ai.bedrock.KnowledgeBaseContextProvider;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AssistantJobServiceTest {

    @Mock AssistantJobStore store;
    @Mock KnowledgeBaseContextProvider knowledgeBaseContextProvider;
    @Mock BedrockConverseOrchestrator converseOrchestrator;

    private AssistantJobService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    void createJobStoresPendingThenRunsConverseAndStoresDone() {
        service = new AssistantJobService(store, knowledgeBaseContextProvider, converseOrchestrator);
        when(knowledgeBaseContextProvider.retrieveContext(any())).thenReturn("doc ctx");
        when(converseOrchestrator.converse(any(), any()))
                .thenReturn(BedrockAgentInvokeResponse.of("답변", "sess-1", false));

        final String jobId = service.createJob(new BedrockAgentInvokeRequest("재고 알려줘", null, null, null));

        assertThat(jobId).isNotBlank();
        verify(store).save(eq(jobId), argThat(j -> AssistantJob.PENDING.equals(j.status())));
        verify(store, timeout(3000)).save(eq(jobId),
                argThat(j -> AssistantJob.DONE.equals(j.status()) && j.result() != null));
    }

    @Test
    void createJobStoresErrorWhenConverseThrows() {
        service = new AssistantJobService(store, knowledgeBaseContextProvider, converseOrchestrator);
        when(knowledgeBaseContextProvider.retrieveContext(any())).thenReturn("doc ctx");
        when(converseOrchestrator.converse(any(), any())).thenThrow(new RuntimeException("boom"));

        final String jobId = service.createJob(new BedrockAgentInvokeRequest("재고 알려줘", null, null, null));

        verify(store, timeout(3000)).save(eq(jobId),
                argThat(j -> AssistantJob.ERROR.equals(j.status()) && "boom".equals(j.error())));
    }
}
