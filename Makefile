JAR_VERSION = 2.0.32
VERSION_WITH_AWS_SDK = $(JAR_VERSION).1000
ZIP_URL = https://s3.amazonaws.com/athena-downloads/drivers/JDBC/SimbaAthenaJDBC-$(VERSION_WITH_AWS_SDK)/SimbaAthenaJDBC-$(VERSION_WITH_AWS_SDK).zip

# Source: https://www.pgrs.net/2011/10/30/using-local-jars-with-leiningen/
maven_repository/athena/athena-jdbc/$(JAR_VERSION)/athena-jdbc-${JAR_VERSION}.jar.sha1:
	mkdir -p maven_repository/athena/athena-jdbc/$(JAR_VERSION)/
	cd maven_repository/athena/athena-jdbc/$(JAR_VERSION)/ \
		&& curl $(ZIP_URL) --output athena-jdbc-${JAR_VERSION}.zip \
		&& unzip -jo athena-jdbc-${JAR_VERSION}.zip \
		&& unzip -jo SimbaAthenaJDBC42-${VERSION_WITH_AWS_SDK}.zip \
		&& ln -s AthenaJDBC42.jar athena-jdbc-${JAR_VERSION}.jar \
		&& sha1sum athena-jdbc-${JAR_VERSION}.jar | cut -f "1" -d " " > athena-jdbc-${JAR_VERSION}.jar.sha1 \
		&& rm *.zip

download-jar: maven_repository/athena/athena-jdbc/$(JAR_VERSION)/athena-jdbc-${JAR_VERSION}.jar.sha1
