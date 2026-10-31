package mx.ipn.escom.tt.ensayosbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Calificación en segundo plano (RNF-09). El tamaño fijo del grupo de hilos funciona como el
 * Bulkhead de la sección 4.4.6: nunca hay más de app.ia.hilos llamadas simultáneas al motor de IA;
 * las demás esperan en la cola. También envía en segundo plano los avisos por correo que no deben
 * retrasar la respuesta (aviso de acceso a los demás administradores).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String EJECUTOR_CALIFICACION = "calificacionExecutor";
    public static final String EJECUTOR_CORREO = "correoExecutor";

    @Bean(name = EJECUTOR_CALIFICACION)
    public ThreadPoolTaskExecutor calificacionExecutor(@Value("${app.ia.hilos:10}") int hilos,
                                                       @Value("${app.ia.cola:200}") int cola) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(hilos);
        executor.setMaxPoolSize(hilos);
        executor.setQueueCapacity(cola);
        executor.setThreadNamePrefix("califica-");
        // Al apagar el servidor se esperan las calificaciones en curso; las pendientes se retoman al arrancar
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(70);
        executor.initialize();
        return executor;
    }

    @Bean(name = EJECUTOR_CORREO)
    public ThreadPoolTaskExecutor correoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("correo-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
