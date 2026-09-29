package com.internship.config;

import com.internship.service.automation.AutomationWorker;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/** Only the isolated automation worker runs locally; unrelated production schedulers stay disabled. */
@Component
@Profile("local")
public class LocalAutomationScheduling {
    private final AutomationWorker worker;
    private final ScheduledExecutorService executor=Executors.newScheduledThreadPool(2,r->{
        Thread thread=new Thread(r,"local-automation");thread.setDaemon(true);return thread;
    });
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
    public LocalAutomationScheduling(AutomationWorker worker){this.worker=worker;}
    @EventListener(ApplicationReadyEvent.class)
    public void start(){
        executor.scheduleWithFixedDelay(()->run(worker::scheduled),5,60,TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(()->run(worker::poll),5,1,TimeUnit.SECONDS);
    }
    private void run(Runnable job){try{job.run();}catch(RuntimeException failure){log.warn("Local automation check failed: {}",failure.getClass().getSimpleName());}}
    @PreDestroy public void stop(){executor.shutdownNow();}
}
