create-cluster:
	kind create cluster --name eventstore --config ./app/k8s/local/kind.yaml
	kubectl cluster-info --context kind-eventstore
	docker pull postgres:16.2-alpine3.19

cluster-info:
	kubectl cluster-info --context kind-eventstore

port-forward-db:
	kubectl port-forward postgres-pod 5433:5432 --context kind-eventstore

psql:
	psql -h localhost -p 5433 -U postgres -d eventstore_db

run-app:
	./gradlew app:run

up:
	tilt up

migrate-db:
	./gradlew migration:run

delete-cluster:
	kind delete cluster --name eventstore

reset-cluster: delete-cluster create-cluster

build:
	./gradlew build

sc-pack:
	./gradlew smartcontract:pack

sc-test: sc-pack
	./gradlew smartcontract:test

test-engine:
	./gradlew engine:test

check-kind-config-and-create-cluster:
	@if [ -f ./app/k8s/local/kind.yaml ]; then \
		if ! kind get clusters | grep -q eventstore; then \
			kind create cluster --name eventstore --config ./app/k8s/local/kind.yaml; \
		else \
			echo "Cluster 'eventstore' already exists."; \
		fi\
		else \
			echo "Kind config file not found!" >&2;\
			exit 1; \
	fi 