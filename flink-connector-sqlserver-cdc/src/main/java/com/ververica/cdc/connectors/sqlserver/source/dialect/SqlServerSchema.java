/*
 * Copyright 2023 Ververica Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ververica.cdc.connectors.sqlserver.source.dialect;

import com.ververica.cdc.connectors.sqlserver.table.SqlServerTableFilter;
import org.apache.flink.util.FlinkRuntimeException;

import io.debezium.connector.sqlserver.SqlServerConnection;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.relational.Table;
import io.debezium.relational.TableId;
import io.debezium.relational.Tables;
import io.debezium.relational.history.TableChanges;
import io.debezium.relational.history.TableChanges.TableChange;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A component used to get schema by table path. */
public class SqlServerSchema {

    private final Map<TableId, TableChange> schemasByTableId;

    public SqlServerSchema() {
        this.schemasByTableId = new ConcurrentHashMap<>();
    }

    public TableChange getTableSchema(JdbcConnection jdbc, TableId tableId) {
        // read schema from cache first
        TableChange schema = schemasByTableId.get(tableId);
        if (schema == null) {
            schema = readTableSchema(jdbc, tableId);
            schemasByTableId.put(tableId, schema);
        }
        return schema;
    }

    private TableChange readTableSchema(JdbcConnection jdbc, TableId tableId) {
        SqlServerConnection sqlServerConnection = (SqlServerConnection) jdbc;
        Set<TableId> tableIdSet = new HashSet<>();
        tableIdSet.add(tableId);

        final Map<TableId, TableChange> tableChangeMap = new HashMap<>();
        Tables tables = new Tables();
        tables.overwriteTable(tables.editOrCreateTable(tableId).create());

        try {
            sqlServerConnection.readSchema(
                    tables, tableId.catalog(), tableId.schema(), new SqlServerTableFilter(tableId), null, false);
            Table table = tables.forTable(tableId);
            TableChange tableChange = new TableChange(TableChanges.TableChangeType.CREATE, table);
            tableChangeMap.put(tableId, tableChange);
        } catch (SQLException e) {
            throw new FlinkRuntimeException(
                    String.format("Failed to read schema for table %s ", tableId), e);
        }

        if (!tableChangeMap.containsKey(tableId)) {
            throw new FlinkRuntimeException(
                    String.format("Can't obtain schema for table %s ", tableId));
        }

        return tableChangeMap.get(tableId);
    }
}
