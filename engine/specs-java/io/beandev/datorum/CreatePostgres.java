package io.beandev.datorum;

import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.lang.Exception;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.Config;

public class CreatePostgres {

    private static CreatePostgres instance;
    private CoreV1Api api;

    public CreatePostgres() throws Exception {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        this.api = new CoreV1Api();
        initDatabase();
    }

    public static CreatePostgres getInstance() throws Exception {
        if (instance == null) {
            instance = new CreatePostgres();
        }
        return instance;
    }

    private void initDatabase() throws Exception {

        ensurePostgresServiceExists(api, "default", "postgres-service");

        waitForPostgresServiceReady(api, "default", "postgres-service");

        ensurePostgresPodExists(api, "default", "postgres");

        waitForPostgresPodReady(api, "default", "postgres");

    }

    private static V1Pod createPostgresPodDefinition() {
        return new V1Pod()
                .apiVersion("v1")
                .kind("Pod")
                .metadata(new V1ObjectMeta().name("postgres").labels(Map.of("app", "postgres")))
                .spec(new V1PodSpec()
                        .containers(Collections.singletonList(new V1Container()
                                .name("postgres")
                                .image("postgres:latest")
                                .ports(Collections.singletonList(new V1ContainerPort()
                                        .containerPort(5432)))
                                .env(Arrays.asList(
                                        new V1EnvVar().name("POSTGRES_DB")
                                                .value("eventstore_db"),
                                        new V1EnvVar().name("POSTGRES_USER")
                                                .value("postgres"),
                                        new V1EnvVar().name("POSTGRES_PASSWORD")
                                                .value("password"))))));
    }

    private static V1Service createPostgresServiceDefinition() {
        return new V1Service()
                .apiVersion("v1")
                .kind("Service")
                .metadata(new V1ObjectMeta().name("postgres-service"))
                .spec(new V1ServiceSpec()
                        .type("NodePort")
                        .selector(Map.of("app", "postgres"))
                        .ports(List.of(
                                new V1ServicePort()
                                        .port(5432)
                                        .targetPort(new IntOrString(5432))
                                        .nodePort(30000)
                                        .protocol("TCP"))));
    }

    private static void waitForPostgresServiceReady(CoreV1Api api, String namespace, String serviceName)
            throws Exception {
        long serviceTimeoutSeconds = 30;
        long startTime = System.currentTimeMillis();
        while (true) {
            V1Service service = api.readNamespacedService(serviceName, namespace, null);
            if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null) {
                System.out.println("PostgreSQL Service is ready.");
                break;
            } else {
                long currentTime = System.currentTimeMillis();
                long elapsedSeconds = (currentTime - startTime) / 1000;
                if (elapsedSeconds > serviceTimeoutSeconds) {
                    throw new Exception("Timeout : PostgreSQL Service is not ready in 30 seconds");
                }
                System.out.println("PostgreSQL Service is not ready, waiting...");
                TimeUnit.SECONDS.sleep(5);
            }
        }
    }

    private static void waitForPostgresPodReady(CoreV1Api api, String namespace, String podName) throws Exception {
        long podTimeoutSeconds = 60;
        long startTime = System.currentTimeMillis();
        while (true) {
            V1Pod pod = api.readNamespacedPod(podName, namespace, null);
            if (pod.getStatus() != null && pod.getStatus().getPhase().equals("Running")) {
                System.out.println("PostgreSQL Pod is ready.");
                break;
            } else {
                long currentTime = System.currentTimeMillis();
                long elapsedSeconds = (currentTime - startTime) / 1000;
                if (elapsedSeconds > podTimeoutSeconds) {
                    throw new Exception("Timeout : PostgreSQL Pod is not ready in 1 minute");
                }
                System.out.println("PostgreSQL Pod is not ready, waiting...");
                TimeUnit.SECONDS.sleep(30);
            }
        }
    }

    private static void ensurePostgresPodExists(CoreV1Api api, String namespace, String podName) throws Exception {
        try {
            V1Pod existingPod = api.readNamespacedPod(podName, namespace, null);
            System.out.println("PostgreSQL Pod already exists: " + existingPod.getMetadata().getName());
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                V1Pod postgresPodDef = createPostgresPodDefinition();
                V1Pod createdPod = api.createNamespacedPod(namespace, postgresPodDef, null, null, null, null);
                System.out.println("PostgreSQL Pod created: " + createdPod.getMetadata().getName());
            } else {
                throw e;
            }
        }
    }

    private static void ensurePostgresServiceExists(CoreV1Api api, String namespace, String serviceName)
            throws Exception {
        try {
            V1Service existingService = api.readNamespacedService(serviceName, namespace, null);
            System.out.println("PostgreSQL Service already exists: " + existingService.getMetadata().getName());
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                V1Service postgresServiceDef = createPostgresServiceDefinition();
                V1Service createdService = api.createNamespacedService(namespace, postgresServiceDef, null, null, null,
                        null);
                System.out.println("PostgreSQL Service created: " + createdService.getMetadata().getName());
            } else {
                throw e;
            }
        }
    }

}
