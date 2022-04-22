package liquibase.ext.keyspace.database

import spock.lang.Specification

class KeyspaceDatabaseTest extends Specification {

    def getShortName() {
        expect:
        new KeyspaceDatabase().getShortName() == "keyspace"
    }

    def getDefaultDriver() {
        expect:
        new KeyspaceDatabase().getDefaultDriver(null) == null
        new KeyspaceDatabase().getDefaultDriver("jdbc:mysql://localhost") == null
        new KeyspaceDatabase().getDefaultDriver("jdbc:cassandra://localhost") == null
        new KeyspaceDatabase().getDefaultDriver("jdbc:cassandra://cassandra.us-east-2.amazonaws.com:9142") != null
        new KeyspaceDatabase().getDefaultDriver("jdbc:cassandra://cassandra.ap-northeast-1.amazonaws.com:9142") != null
    }

    def getDefaultDriverWithFips() {
        expect:
        new KeyspaceDatabase().getDefaultDriver("jdbc:cassandra://cassandra-fips.us-east-1.amazonaws.com:9142") != null
        new KeyspaceDatabase().getDefaultDriver("jdbc:cassandra://cassandra-fips.us-west-2.amazonaws.com:9142") != null
    }

    def settingAutoCommitShouldNotChangeAutoCommit(){
        expect:
        def db = new KeyspaceDatabase()
        db.setAutoCommit(false)
        db.isAutoCommit()
    }
}
