INPUT_FILE ?= domain

validate:
	./gradlew validateCML -PinputFile=../$(INPUT_FILE)

validate-all:
	./gradlew validateAllCML -PcmlDir=../../

generate-context-map:
	./gradlew generateContextMap -PinputFile=../$(INPUT_FILE) -PoutputDir=../output-domain
