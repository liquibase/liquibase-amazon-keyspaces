package liquibase.ext.keyspace

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.change.CheckSum
import liquibase.changelog.ChangeLogHistoryServiceFactory
import liquibase.ext.keyspace.database.KeyspaceDatabase
import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.ClassLoaderResourceAccessor
import spock.lang.Specification


class KeyspaceFunctionalIT extends Specification {

    def url = "jdbc:cassandra://cassandra.us-east-1.amazonaws.com:9142;DefaultKeyspace=betterbotz;UID=<username>;PWD=<password>;SSLMode=1;AuthMech=1;SSLTruststorePath=./cassandra_truststore.jks;SSLTrustStorePwd=testpasswd;LogLevel=6;TunableConsistency=6;"
    def defaultSchemaName = "betterbotz"
    def username = System.getProperty("keyspace_username")
    def password = System.getProperty("keyspace_password")
    def database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(), url, username, password, null, null, defaultSchemaName, false, false, null, null, null, null, null, null, null)

    def "parseKeyspaceUrl"() {
        expect:
        def driver = new KeyspaceDatabase().getDefaultDriver(url)
        driver == "com.simba.cassandra.jdbc42.Driver"
    }

    def "createKeyspaceConnection"() {
        expect:
        database.getShortName() == "keyspace"
    }

    def "warnKeyspaceDoesNotExist"() {
        when:
        def ExceptionMessage = ""
        // incorrect_url is set to wrong region.
        def incorrect_url = "jdbc:cassandra://cassandra.us-east-2.amazonaws.com:9142;DefaultKeyspace=betterbotz;SSLMode=1;AuthMech=1;UID=ZacClifton-at-048962136615;PWD=Tb8TV8hA1t+DhyrzRl85OwH/fR97pPhy3z1zv7K78AA=;SSLTruststorePath=./cassandra_truststore.jks;SSLTrustStorePwd=testpasswd;LogLevel=6;TunableConsistency=6;"
        def keyspace_name = "betterbotz"
        try {
            CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(), incorrect_url, null, null, null, null, defaultSchemaName, false, false, null, null, null, null, null, null, null)
        } catch (Exception e) {
            ExceptionMessage = e.message
        }
        then:
        assert ExceptionMessage.contains(keyspace_name)
    }

    def "calculateCheckSum"() {
        when:
        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
        CheckSum checkSum = liquibase.calculateCheckSum("test-changelog.xml", "1", "betterbotz")
        then:
        //TODO: need seperate changelog that actual has some stuff that needs to be sync'd
        assert checkSum.toString().trim() == "8:80f1a851837367ff74bdb07075c716af"

    }

    def "changeLogSync"() {

        when:
        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
        liquibase.changeLogSync((Contexts) null, (LabelExpression) null)
        then:
        database != null

    }

    // TODO: need to make clearCheckSums extendable
    //  https://github.com/liquibase/liquibase/issues/1472
//    def "clearCheckSums"() {
//
//        when:
//        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
//        def stringWriter = new StringWriter()
//        liquibase.clearCheckSums();
//        then:
//        database != null;
//
//    }

    def "update"() {
        when:
        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
        liquibase.update((Contexts) null)
        then:
        database != null

    }

    def "changeLogSyncSQL"() {

        when:
        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
        def stringWriter = new StringWriter()
        liquibase.changeLogSync((Contexts) null, (LabelExpression) null, stringWriter)
        then:
        assert stringWriter.toString().contains("UPDATE betterbotz.databasechangeloglock SET LOCKED = TRUE")

    }

    def "status"() {

        when:
        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
        def statusOutput = new StringWriter()
        liquibase.reportStatus(false, (Contexts) null, statusOutput)
        then:
        statusOutput.toString().trim() == "@jdbc:cassandra://localhost:9042/betterbotz;DefaultKeyspace=betterbotz is up to date"

    }

    def "status2"() {

        when:
        def historyService = ChangeLogHistoryServiceFactory.getInstance().getChangeLogService(database)
        def ranChangeSets = historyService.getRanChangeSets()
        then:
        ranChangeSets.size() == 3

    }

    def "dbDoc"() {

        when:
        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
        liquibase.generateDocumentation("target")
        then:
        database != null

    }

//    def "rollbackCount1"() {
//
//        when:
//            def liquibase = new Liquibase("rollback.changelog.sql", new ClassLoaderResourceAccessor(), database)
//            liquibase.update((Contexts) null)
//            def output = new StringWriter()
//            liquibase.rollback(1, (String) null, output)
//        then:
//            assert output.toString().trim().contains("DROP TABLE betterbotz.TESTME5")
//        when:
//            liquibase.rollback(1, (String) null)
//        then:
//            database != null
//    }

}
