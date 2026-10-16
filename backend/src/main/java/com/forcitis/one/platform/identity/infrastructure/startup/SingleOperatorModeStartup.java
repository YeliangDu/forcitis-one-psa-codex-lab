package com.forcitis.one.platform.identity.infrastructure.startup;

import com.forcitis.one.platform.audit.api.AuditLog;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * ADR-0030 section 1 items 4 and 7. With the mode on, every DataSource is checked the moment it is made, before any bean can use it, so nothing
 * (process engine tables, event registry, scheduler locks, operations command, web server) has touched a wrong database. The database and the session
 * user are a whitelist. The announcement (one WARN line, one audit entry) is written before the context finishes refreshing.
 */
final class SingleOperatorModeStartup implements BeanPostProcessor {
    static final String DATABASE = "forcitis_one_dev";
    static final String USER_PREFIX = "one_dev_";

    private final boolean enabled;
    private final String profiles;
    private final ObjectProvider<AuditLog> audit;
    private volatile String database;

    SingleOperatorModeStartup(boolean enabled, String profiles, ObjectProvider<AuditLog> audit) {
        this.enabled = enabled;
        this.profiles = profiles;
        this.audit = audit;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (enabled && bean instanceof DataSource dataSource) {
            database = verify(dataSource);
        }
        return bean;
    }

    private static String verify(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("select current_database(), session_user")) {
            row.next();
            String database = row.getString(1);
            String user = row.getString(2);
            if (!DATABASE.equals(database) || !user.startsWith(USER_PREFIX)) {
                throw new IllegalStateException(SingleOperatorModeListener.KEY + ": the value true needs the database " + DATABASE
                        + " and a session user whose name starts with " + USER_PREFIX + " (ADR-0030)");
            }
            return database;
        } catch (SQLException failure) {
            throw new IllegalStateException(SingleOperatorModeListener.KEY + ": the database check could not run (ADR-0030)", failure);
        }
    }
}
