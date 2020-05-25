package core.framework.internal.db;

import com.mysql.cj.protocol.Resultset;
import com.mysql.cj.protocol.ServerSession;
import core.framework.db.Database;
import core.framework.internal.log.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author neo
 */
class MySQLQueryInterceptorTest {
    private MySQLQueryInterceptor interceptor;
    private LogManager logManager;

    @BeforeEach
    void createMySQLQueryInterceptor() {
        logManager = new LogManager();
        interceptor = new MySQLQueryInterceptor();

        logManager.begin("begin");
    }

    @AfterEach
    void cleanup() {
        logManager.end("end");
    }

    @Test
    void defaultBehaviors() {
        assertThat(interceptor.init(null, null, null)).isSameAs(interceptor);
        Resultset result = interceptor.preProcess(null, null);
        assertThat(result).isNull();
        assertThat(interceptor.executeTopLevelOnly()).isTrue();
        interceptor.destroy();
    }

    @Test
    void postProcess() {
        ServerSession session = mock(ServerSession.class);
        when(session.noGoodIndexUsed()).thenReturn(Boolean.TRUE);
        interceptor.postProcess(() -> "sql", null, null, session);

        Database.enableSlowSQLWarning(false);
        interceptor.postProcess(() -> "sql", null, null, session);
        Database.enableSlowSQLWarning(true);
    }
}