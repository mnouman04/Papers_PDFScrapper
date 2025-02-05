# Running the Java Scraper

### **Setup Maven Project and Install Dependencies**

To run the Java scraper, follow these steps:

1. **Create a Maven Project**  
   If you don’t have a Maven project, create one using:
   ```bash
   mvn archetype:generate -DgroupId=com.scraper -DartifactId=NeurIPS-Scraper -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
   ```

2. **Add Dependencies**
   Open `pom.xml` and add the following dependencies:
   ```xml
   <dependencies>
       <dependency>
           <groupId>org.jsoup</groupId>
           <artifactId>jsoup</artifactId>
           <version>1.15.3</version>
       </dependency>
   </dependencies>
   ```

3. **Build the Project**  
   ```bash
   mvn clean install
   ```

4. **Run the Java Scraper**  
   ```bash
   mvn exec:java -Dexec.mainClass="com.scraper.NipsScraper"
   ```