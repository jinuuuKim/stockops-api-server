package com.stockops.ai.bedrock.job;

import com.stockops.ai.bedrock.BedrockConverseOrchestrator;
import com.stockops.ai.bedrock.KnowledgeBaseContextProvider;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeRequest;
import com.stockops.ai.bedrock.dto.BedrockAgentInvokeResponse;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Runs assistant conversations asynchronously: a job is created and its id returned immediately,
 * while the (potentially long) Converse tool-use loop executes on a worker thread and writes its
 * result to {@link AssistantJobStore} for the client to poll.
 *
 * <p>This decouples the slow LLM call from a single held-open HTTP request, so it no longer fights
 * the frontend axios timeout or any CDN/load-balancer read timeout.
 *
 * @author StockOps Team
 * @since 2.7
 */
@Service
public class AssistantJobService {

    private static final Logger log = LoggerFactory.getLogger(AssistantJobService.class);
    private static final int WORKER_THREADS = 4;

    private final AssistantJobStore store;
    private final KnowledgeBaseContextProvider knowledgeBaseContextProvider;
    private final BedrockConverseOrchestrator converseOrchestrator;
    private final ExecutorService executor;

    public AssistantJobService(final AssistantJobStore store,
                               final KnowledgeBaseContextProvider knowledgeBaseContextProvider,
                               final BedrockConverseOrchestrator converseOrchestrator) {
        this.store = store;
        this.knowledgeBaseContextProvider = knowledgeBaseContextProvider;
        this.converseOrchestrator = converseOrchestrator;
        this.executor = Executors.newFixedThreadPool(WORKER_THREADS, runnable -> {
            final Thread thread = new Thread(runnable, "assistant-job");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Records a PENDING job, schedules the conversation on a worker thread, and returns the job id
     * immediately. The caller's authentication is captured and re-applied on the worker so scope
     * checks behave identically to a synchronous request.
     *
     * @param request assistant request (message + optional session/scope)
     * @return the new job id to poll
     */
    public String createJob(final BedrockAgentInvokeRequest request) {
        final String jobId = UUID.randomUUID().toString();
        store.save(jobId, AssistantJob.pending());
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        executor.submit(() -> runJob(jobId, request, authentication));
        return jobId;
    }

    /**
     * Returns the current state of a job.
     *
     * @param jobId job identifier
     * @return the job state, or {@code null} when unknown/expired
     */
    public AssistantJob getJob(final String jobId) {
        return store.load(jobId);
    }

    private void runJob(final String jobId, final BedrockAgentInvokeRequest request,
                        final Authentication authentication) {
        // The default thread-local SecurityContext is NOT inherited by worker threads, so re-apply
        // the caller's auth here; otherwise ScopeGuard would see no principal and reject/leak scope.
        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            final String documentContext = knowledgeBaseContextProvider.retrieveContext(request.message());
            final BedrockAgentInvokeResponse response = converseOrchestrator.converse(request, documentContext);
            store.save(jobId, AssistantJob.done(response));
        } catch (final Exception e) {
            log.error("[AssistantJob] job {} failed: {}", jobId, e.getMessage(), e);
            store.save(jobId, AssistantJob.failed(e.getMessage() != null ? e.getMessage() : "internal error"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
