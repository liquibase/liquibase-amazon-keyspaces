package liquibase.ext.cassandra

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.ext.cassandra.database.CassandraDatabase
import liquibase.integration.commandline.CommandLineUtils
import liquibase.resource.ClassLoaderResourceAccessor
import spock.lang.Specification

class KeyspaceFunctionalityTest extends Specification {

    def defaultSchemaName = "betterbotz"
    def username = System.getProperty("keyspace_username")
    def password = System.getProperty("keyspace_password")
    def url = "jdbc:cassandra://cassandra.us-east-1.amazonaws.com:9142;DefaultKeyspace=betterbotz;SSLMode=1;AuthMech=1;UID=ZacClifton-at-048962136615;PWD=Tb8TV8hA1t+DhyrzRl85OwH/fR97pPhy3z1zv7K78AA=;SSLTruststorePath=./cassandra_truststore.jks;SSLTrustStorePwd=testpasswd;LogLevel=6;TunableConsistency=6;"

    def "parseKeyspaceUrl"(){
        when:
        def dbString = new CassandraDatabase().getDefaultDriver(url)
        then:
        dbString != null
    }

    def "createKeyspaceConnection"(){

        when:
        def dbString = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(), url, null, null, null, null, defaultSchemaName, false, false, null, null, null, null, null, null, null)
        then:
        dbString != null
    }

    def "warnKeyspaceDoesNotExist"(){
        when:
        def ExceptionMessage = ""
        // incorrect_url is set to wrong region.
        def incorrect_url = "jdbc:cassandra://cassandra.us-east-2.amazonaws.com:9142;DefaultKeyspace=betterbotz;SSLMode=1;AuthMech=1;UID=ZacClifton-at-048962136615;PWD=Tb8TV8hA1t+DhyrzRl85OwH/fR97pPhy3z1zv7K78AA=;SSLTruststorePath=./cassandra_truststore.jks;SSLTrustStorePwd=testpasswd;LogLevel=6;TunableConsistency=6;"
        def keyspace_name = "betterbotz"
        try {
            CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(), incorrect_url, null, null, null, null, defaultSchemaName, false, false, null, null, null, null, null, null, null)
        }catch(Exception e){
            ExceptionMessage = e.message
        }
        then:
        assert ExceptionMessage.contains(keyspace_name)

    }

    def "runKeyspaceChangeSet"(){
        when:
        def database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(), url, null, null, null, null, defaultSchemaName, false, false, null, null, null, null, null, null, null)
        def liquibase = new Liquibase("test-changelog.xml", new ClassLoaderResourceAccessor(), database)
        liquibase.update((Contexts) null)
        then:
        assert database != null
    }

}
