FROM openjdk:21
WORKDIR /var/jenkins_home/workspace/AINoliterSSH/target/
COPY demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 80:80
ENTRYPOINT ["java", "-jar", "app.jar"]
