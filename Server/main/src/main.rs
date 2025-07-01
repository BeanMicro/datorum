use std::collections::HashMap;
use std::pin::Pin;
use tokio::sync::mpsc;
use tokio_stream::{wrappers::ReceiverStream, Stream};
use tonic::{transport::Server, Request, Response, Status};
use prost::Message;

// Manual protobuf message definitions
#[derive(Clone, PartialEq, Message)]
pub struct HealthCheckRequest {
    #[prost(string, tag = "1")]
    pub service: String,
}

#[derive(Clone, PartialEq, Message)]
pub struct HealthCheckResponse {
    #[prost(enumeration = "health_check_response::ServingStatus", tag = "1")]
    pub status: i32,
}

pub mod health_check_response {
    #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
    #[repr(i32)]
    pub enum ServingStatus {
        Unknown = 0,
        Serving = 1,
        NotServing = 2,
    }

    impl Default for ServingStatus {
        fn default() -> Self {
            ServingStatus::Unknown
        }
    }

    impl From<ServingStatus> for i32 {
        fn from(status: ServingStatus) -> i32 {
            status as i32
        }
    }

    impl TryFrom<i32> for ServingStatus {
        type Error = ();
        fn try_from(value: i32) -> Result<Self, Self::Error> {
            match value {
                0 => Ok(ServingStatus::Unknown),
                1 => Ok(ServingStatus::Serving),
                2 => Ok(ServingStatus::NotServing),
                _ => Err(()),
            }
        }
    }
}

#[derive(Clone, PartialEq, Message)]
pub struct QueryRequest {
    #[prost(string, tag = "1")]
    pub query: String,
    #[prost(string, repeated, tag = "2")]
    pub parameters: Vec<String>,
}

#[derive(Clone, PartialEq, Message)]
pub struct QueryResponse {
    #[prost(bool, tag = "1")]
    pub success: bool,
    #[prost(string, tag = "2")]
    pub error_message: String,
    #[prost(message, repeated, tag = "3")]
    pub results: Vec<QueryResult>,
}

#[derive(Clone, PartialEq, Message)]
pub struct QueryResult {
    #[prost(map = "string, string", tag = "1")]
    pub row: HashMap<String, String>,
}

// Service implementation
#[derive(Debug, Default, Clone)]
pub struct DatorumService {}

#[tonic::async_trait]
impl datorum_service_server::DatorumService for DatorumService {
    async fn health_check(
        &self,
        request: Request<HealthCheckRequest>,
    ) -> Result<Response<HealthCheckResponse>, Status> {
        println!("Health check request from: {}", request.get_ref().service);

        let response = HealthCheckResponse {
            status: health_check_response::ServingStatus::Serving as i32,
        };

        Ok(Response::new(response))
    }

    async fn execute_query(
        &self,
        request: Request<QueryRequest>,
    ) -> Result<Response<QueryResponse>, Status> {
        let query_req = request.get_ref();
        println!("Execute query: {}", query_req.query);

        // Simulate query execution
        let mut results = Vec::new();
        if query_req.query.contains("users") {
            let mut row1 = HashMap::new();
            row1.insert("id".to_string(), "1".to_string());
            row1.insert("name".to_string(), "Alice".to_string());
            row1.insert("email".to_string(), "alice@example.com".to_string());

            let mut row2 = HashMap::new();
            row2.insert("id".to_string(), "2".to_string());
            row2.insert("name".to_string(), "Bob".to_string());
            row2.insert("email".to_string(), "bob@example.com".to_string());

            results.push(QueryResult { row: row1 });
            results.push(QueryResult { row: row2 });
        }

        let response = QueryResponse {
            success: true,
            error_message: String::new(),
            results,
        };

        Ok(Response::new(response))
    }

    type StreamQueryStream = Pin<Box<dyn Stream<Item = Result<QueryResult, Status>> + Send>>;

    async fn stream_query(
        &self,
        request: Request<QueryRequest>,
    ) -> Result<Response<Self::StreamQueryStream>, Status> {
        let query_req = request.into_inner();
        println!("Stream query: {}", query_req.query);

        let (tx, rx) = mpsc::channel(4);

        // Simulate streaming query results
        tokio::spawn(async move {
            if query_req.query.contains("users") {
                let mut row1 = HashMap::new();
                row1.insert("id".to_string(), "1".to_string());
                row1.insert("name".to_string(), "Alice".to_string());

                let mut row2 = HashMap::new();
                row2.insert("id".to_string(), "2".to_string());
                row2.insert("name".to_string(), "Bob".to_string());

                let mut row3 = HashMap::new();
                row3.insert("id".to_string(), "3".to_string());
                row3.insert("name".to_string(), "Charlie".to_string());

                let results = vec![
                    QueryResult { row: row1 },
                    QueryResult { row: row2 },
                    QueryResult { row: row3 },
                ];

                for result in results {
                    if tx.send(Ok(result)).await.is_err() {
                        break;
                    }
                    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
                }
            }
        });

        let stream = ReceiverStream::new(rx);
        Ok(Response::new(Box::pin(stream) as Self::StreamQueryStream))
    }
}

// Manual service trait and server implementation
pub mod datorum_service_server {
    use super::*;
    use tonic::codegen::*;

    #[tonic::async_trait]
    pub trait DatorumService: Send + Sync + 'static {
        async fn health_check(
            &self,
            request: Request<HealthCheckRequest>,
        ) -> Result<Response<HealthCheckResponse>, Status>;

        async fn execute_query(
            &self,
            request: Request<QueryRequest>,
        ) -> Result<Response<QueryResponse>, Status>;

        type StreamQueryStream: Stream<Item = Result<QueryResult, Status>> + Send + 'static;

        async fn stream_query(
            &self,
            request: Request<QueryRequest>,
        ) -> Result<Response<Self::StreamQueryStream>, Status>;
    }

    #[derive(Debug)]
    pub struct DatorumServiceServer<T> {
        inner: std::sync::Arc<T>,
        accept_compression_encodings: EnabledCompressionEncodings,
        send_compression_encodings: EnabledCompressionEncodings,
    }

    struct _Inner<T>(std::sync::Arc<T>);

    impl<T: DatorumService> DatorumServiceServer<T> {
        pub fn new(inner: T) -> Self {
            Self::from_arc(std::sync::Arc::new(inner))
        }

        pub fn from_arc(inner: std::sync::Arc<T>) -> Self {
            let inner = _Inner(inner);
            Self {
                inner: inner.0,
                accept_compression_encodings: Default::default(),
                send_compression_encodings: Default::default(),
            }
        }

        pub fn with_interceptor<F>(
            inner: T,
            interceptor: F,
        ) -> InterceptedService<Self, F>
        where
            F: tonic::service::Interceptor,
        {
            InterceptedService::new(Self::new(inner), interceptor)
        }

        pub fn accept_compressed(mut self, encoding: CompressionEncoding) -> Self {
            self.accept_compression_encodings.enable(encoding);
            self
        }

        pub fn send_compressed(mut self, encoding: CompressionEncoding) -> Self {
            self.send_compression_encodings.enable(encoding);
            self
        }
    }

    impl<T, B> tonic::codegen::Service<http::Request<B>> for DatorumServiceServer<T>
    where
        T: DatorumService,
        B: Body + Send + 'static,
        B::Error: Into<StdError> + Send + 'static,
    {
        type Response = http::Response<tonic::body::BoxBody>;
        type Error = std::convert::Infallible;
        type Future = BoxFuture<Self::Response, Self::Error>;

        fn poll_ready(
            &mut self,
            _cx: &mut Context<'_>,
        ) -> Poll<Result<(), Self::Error>> {
            Poll::Ready(Ok(()))
        }

        fn call(&mut self, req: http::Request<B>) -> Self::Future {
            let inner = self.inner.clone();
            match req.uri().path() {
                "/datorum.DatorumService/HealthCheck" => {
                    #[allow(non_camel_case_types)]
                    struct HealthCheckSvc<T: DatorumService>(pub std::sync::Arc<T>);
                    impl<T: DatorumService> tonic::server::UnaryService<HealthCheckRequest>
                        for HealthCheckSvc<T>
                    {
                        type Response = HealthCheckResponse;
                        type Future = BoxFuture<
                            tonic::Response<Self::Response>,
                            tonic::Status,
                        >;
                        fn call(
                            &mut self,
                            request: tonic::Request<HealthCheckRequest>,
                        ) -> Self::Future {
                            let inner = self.0.clone();
                            let fut = async move { (*inner).health_check(request).await };
                            Box::pin(fut)
                        }
                    }
                    let accept_compression_encodings = self.accept_compression_encodings;
                    let send_compression_encodings = self.send_compression_encodings;
                    let inner = self.inner.clone();
                    let fut = async move {
                        let inner = inner;
                        let method = HealthCheckSvc(inner);
                        let codec = tonic::codec::ProstCodec::default();
                        let mut grpc = tonic::server::Grpc::new(codec)
                            .apply_compression_config(
                                accept_compression_encodings,
                                send_compression_encodings,
                            );
                        let res = grpc.unary(method, req).await;
                        Ok(res)
                    };
                    Box::pin(fut)
                }
                "/datorum.DatorumService/ExecuteQuery" => {
                    #[allow(non_camel_case_types)]
                    struct ExecuteQuerySvc<T: DatorumService>(pub std::sync::Arc<T>);
                    impl<T: DatorumService> tonic::server::UnaryService<QueryRequest>
                        for ExecuteQuerySvc<T>
                    {
                        type Response = QueryResponse;
                        type Future = BoxFuture<
                            tonic::Response<Self::Response>,
                            tonic::Status,
                        >;
                        fn call(
                            &mut self,
                            request: tonic::Request<QueryRequest>,
                        ) -> Self::Future {
                            let inner = self.0.clone();
                            let fut = async move { (*inner).execute_query(request).await };
                            Box::pin(fut)
                        }
                    }
                    let accept_compression_encodings = self.accept_compression_encodings;
                    let send_compression_encodings = self.send_compression_encodings;
                    let inner = self.inner.clone();
                    let fut = async move {
                        let inner = inner;
                        let method = ExecuteQuerySvc(inner);
                        let codec = tonic::codec::ProstCodec::default();
                        let mut grpc = tonic::server::Grpc::new(codec)
                            .apply_compression_config(
                                accept_compression_encodings,
                                send_compression_encodings,
                            );
                        let res = grpc.unary(method, req).await;
                        Ok(res)
                    };
                    Box::pin(fut)
                }
                "/datorum.DatorumService/StreamQuery" => {
                    #[allow(non_camel_case_types)]
                    struct StreamQuerySvc<T: DatorumService>(pub std::sync::Arc<T>);
                    impl<T: DatorumService>
                        tonic::server::ServerStreamingService<QueryRequest>
                        for StreamQuerySvc<T>
                    {
                        type Response = QueryResult;
                        type ResponseStream = T::StreamQueryStream;
                        type Future = BoxFuture<
                            tonic::Response<Self::ResponseStream>,
                            tonic::Status,
                        >;
                        fn call(
                            &mut self,
                            request: tonic::Request<QueryRequest>,
                        ) -> Self::Future {
                            let inner = self.0.clone();
                            let fut = async move { (*inner).stream_query(request).await };
                            Box::pin(fut)
                        }
                    }
                    let accept_compression_encodings = self.accept_compression_encodings;
                    let send_compression_encodings = self.send_compression_encodings;
                    let inner = self.inner.clone();
                    let fut = async move {
                        let inner = inner;
                        let method = StreamQuerySvc(inner);
                        let codec = tonic::codec::ProstCodec::default();
                        let mut grpc = tonic::server::Grpc::new(codec)
                            .apply_compression_config(
                                accept_compression_encodings,
                                send_compression_encodings,
                            );
                        let res = grpc.server_streaming(method, req).await;
                        Ok(res)
                    };
                    Box::pin(fut)
                }
                _ => {
                    Box::pin(async move {
                        Ok(http::Response::builder()
                            .status(200)
                            .header("grpc-status", "12")
                            .header("content-type", "application/grpc")
                            .body(empty_body())
                            .unwrap())
                    })
                }
            }
        }
    }

    impl<T: DatorumService> Clone for DatorumServiceServer<T> {
        fn clone(&self) -> Self {
            let inner = self.inner.clone();
            Self {
                inner,
                accept_compression_encodings: self.accept_compression_encodings,
                send_compression_encodings: self.send_compression_encodings,
            }
        }
    }

    impl<T: DatorumService> Clone for _Inner<T> {
        fn clone(&self) -> Self {
            Self(self.0.clone())
        }
    }

    impl<T: std::fmt::Debug> std::fmt::Debug for _Inner<T> {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            write!(f, "{:?}", self.0)
        }
    }

    impl<T: DatorumService> tonic::server::NamedService for DatorumServiceServer<T> {
        const NAME: &'static str = "datorum.DatorumService";
    }
}

#[tokio::main]
pub async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let addr = "[::1]:50051".parse::<std::net::SocketAddr>()?;
    let datorum_service = DatorumService::default();

    println!("Datorum server starting on {}", addr);

    // Create proper gRPC server using tonic
    Server::builder()
        .add_service(datorum_service_server::DatorumServiceServer::new(datorum_service))
        .serve(addr)
        .await?;

    Ok(())
}
