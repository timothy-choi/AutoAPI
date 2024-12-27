var BigTableHelper = require('./BigTableOperationsHelper');

exports.createInstance = async (req, res) => {
    try {
        var instanceResponse = await BigTableHelper.createInstance(req.body.options, req.body.clusters, req.body.instanceId);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateInstance = async (req, res) => {
    try {
        var instanceResponse = await BigTableHelper.updateInstance(req.body.instanceId, req.body.options);

        return res.status(200).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteInstance = async (req, res) => {
    try {
        await BigTableHelper.deleteInstance(req.instanceId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getInstance = async (req, res) => {
    try {
        var instanceInfo = await BigTableHelper.getInstance(req.instanceId);

        return res.status(200).send({"instanceInfo": instanceInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createCluster = async (req, res) => {
    try {
        var clusterResponse = await BigTableHelper.createCluster(req.body.instanceId, req.body.clusterId, req.body.options);

        return res.status(201).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateCluster = async (req, res) => {
    try {
        var clusterResponse = await BigTableHelper.updateCluster(req.body.instanceId, req.body.clusterId, req.body.options);

        return res.status(200).send({"clusterResponse": clusterResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteCluster = async (req, res) => {
    try {
        await BigTableHelper.deleteCluster(req.instanceId, req.instanceId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getCluster = async (req, res) => {
    try {
        var clusterInfo = await BigTableHelper.getClusterInfo(req.instanceId, req.clusterId);

        return res.status(200).send({"clusterInfo": clusterInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createTable = async (req, res) => {
    try {
        var tableResponse = await BigTableHelper.createTable(req.body.instanceId, req.body.tableId, req.body.columnFamilies);

        return res.status(201).send({"tableResponse": tableResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateTableColumnFamily = async (req, res) => {
    try {
        var instanceResponse = await BigTableHelper.updateTableColumnFamily(req.body.instanceId, req.body.tableId, req.body.columnFamilyId, req.body.options);

        return res.status(200).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.checkIfFamilyColumnExists = async (req, res) => {
    try {
        var exists = await BigTableHelper.checkColumnFamilyExists(req.instanceId, req.tableId, req.columnFamilyName);

        return res.status(200).send({"exists": exists});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteTableColumnFamily = async (req, res) => {
    try {
        await BigTableHelper.deleteColumnFamily(req.instanceId, req.tableId, req.columnFamilyId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.checkIfTableExists = async (req, res) => {
    try {
        var exists = await BigTableHelper.checkTableExists(req.instanceId, req.tableId);

        return res.status(200).send({"exists": exists});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteTable = async (req, res) => {
    try {
        await BigTableHelper.deleteTable(req.instanceId, req.tableId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.queryRowsByPrefix = async (req, res) => {
    try {
        var queryResult = await BigTableHelper.getRowsByPrefix(req.instanceId, req.tableId, req.rowKeyPrefix);

        return res.status(200).send({"queryResult": queryResult});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.queryRowsByColumnFamily = async (req, res) => {
    try {
        var queryResult = await BigTableHelper.getRowsByColumnFamily(req.instanceId, req.tableId, req.columnFamilyId);

        return res.status(200).send({"queryResult": queryResult});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.querySpecificRow = async (req, res) => {
    try {
        var queryResult = await BigTableHelper.getSpecificRow(req.instanceId, req.tableId, req.rowKey);

        return res.status(200).send({"queryResult": queryResult});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.queryFilteredRows = async (req, res) => {
    try {
        var queryResult = await BigTableHelper.getFilteredRows(req.instanceId, req.tableId, req.rowKeyPrefix, req.columnFamilyId);

        return res.status(200).send({"queryResult": queryResult});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.queryColumnsByQualifier = async (req, res) => {
    try {
        var queryResult = await BigTableHelper.getColumnsByQualifier(req.instanceId, req.tableId, req.columnFamilyId, req.columnQualifier);

        return res.status(200).send({"queryResult": queryResult});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.queryRowsByValue = async (req, res) => {
    try {
        var queryResult = await BigTableHelper.getFilteredRowsByValue(req.instanceId, req.tableId, req.columnFamilyId, req.columnQualifier, req.value);

        return res.status(200).send({"queryResult": queryResult});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.insertData = async (req, res) => {
    try {
        await BigTableHelper.insertData(req.body.instanceId, req.body.tableId, req.body.rowKey, req.body.data);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.insertMultipleRows = async (req, res) => {
    try {
        await BigTableHelper.insertMultipleRows(req.body.instanceId, req.body.tableId, req.body.rowsData);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.insertWithCondition = async (req, res) => {
    try {
        await BigTableHelper.insertWithCondition(req.body.instanceId, req.body.tableId, req.body.rowKey, req.body.data, req.body.condition);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateSingleRow = async (req, res) => {
    try {
        await BigTableHelper.updateSingleRow(req.body.instanceId, req.body.tableId, req.body.rowKey, req.body.mutations);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateMultipleRows = async (req, res) => {
    try {
        await BigTableHelper.updateMultipleRows(req.body.instanceId, req.body.tableId, req.body.rowsData);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.conditionalUpdate = async (req, res) => {
    try {
        await BigTableHelper.conditionalUpdate(req.body.instanceId, req.body.tableId, req.body.rowKey, req.body.filter, req.body.mutations);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.incrementCellValue = async (req, res) => {
    try {
        await BigTableHelper.incrementCellValue(req.body.instanceId, req.body.tableId, req.body.rowKey, req.body.columnFamily, req.body.columnQualifier, req.body.incrementValue);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteAndUpdateRow = async (req, res) => {
    try {
        await BigTableHelper.deleteAndUpdateRow(req.body.instanceId, req.body.tableId, req.body.rowKey, req.body.newData);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteRow = async (req, res) => {
    try {
        await BigTableHelper.deleteRow(req.instanceId, req.tableId, req.rowKey);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteCells = async (req, res) => {
    try {
        await BigTableHelper.deleteCells(req.instanceId, req.tableId, req.rowKey, req.columnFamily);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteRows = async (req, res) => {
    try {
        await BigTableHelper.deleteRows(req.body.instanceId, req.body.tableId, req.body.rowKeys);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteRowsByPrefix = async (req, res) => {
    try {
        await BigTableHelper.deleteRowsByPrefix(req.instanceId, req.tableId, req.rowKeyPrefix);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};