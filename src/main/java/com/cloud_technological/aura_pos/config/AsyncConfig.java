package com.cloud_technological.aura_pos.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "facturaLogExecutor")
    public Executor facturaLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("factura-log-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean(name = "errorLogExecutor")
    public Executor errorLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("error-log-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * Pool de los procesos de nómina (Fase 7).
     *
     * <p><b>Por qué uno propio y no el default:</b> con varios beans
     * {@code Executor} presentes, {@code TaskExecutionAutoConfiguration} se
     * desactiva ({@code @ConditionalOnMissingBean(Executor.class)}) y no existe
     * {@code applicationTaskExecutor}. Un {@code @Async} sin calificar cae
     * entonces en {@code SimpleAsyncTaskExecutor}: <b>un hilo nuevo por
     * invocación, sin límite</b>. Cincuenta liquidaciones concurrentes serían
     * cincuenta hilos, cada uno abriendo conexiones a la BD.
     *
     * <p>Por eso los jobs usan {@code @Async("nominaExecutor")} explícito.
     *
     * <p><b>Dimensionamiento:</b> pool chico a propósito. Una liquidación es
     * larga y usa conexiones de BD; más paralelismo agotaría el pool de
     * conexiones antes de ir más rápido. La cola absorbe los picos.
     *
     * <p>{@code CallerRunsPolicy}: si la cola se llena, el llamador ejecuta la
     * tarea él mismo en vez de descartarla. Un proceso de nómina descartado
     * dejaría su fila en PENDIENTE para siempre.
     */
    @Bean(name = "nominaExecutor")
    public Executor nominaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("nomina-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Esperar a que terminen: cortar una liquidación a medias dejaría
        // nóminas liquidadas y otras no, sin que nadie sepa cuáles.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
