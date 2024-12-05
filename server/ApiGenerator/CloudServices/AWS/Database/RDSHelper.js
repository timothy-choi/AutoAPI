const AWS = require('aws-sdk');

exports.createRDSInstance = async (dbInstanceInfo, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const instanceData = await rds.createDBInstance(dbInstanceInfo).promise();

    let status = "creating";
    while (status === "creating") {
        await new Promise((resolve) => setTimeout(resolve, 15000));
        const data = await rds.describeDBInstances({ DBInstanceIdentifier:  dbInstanceInfo.DBInstanceIdentifier}).promise();
        status = data.DBInstances[0].DBInstanceStatus;
    }

    if (status === "available") {
        return instanceData;
    } else {
        throw new Error(`Instance creation failed with status: ${status}`);
    }
}

exports.describeRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });
    
    const data = await rds.describeDBInstances({DBInstanceIdentifier: currDbId}).promise();

    return data.DBInstances[0];
}

exports.startRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        DBInstanceIdentifier: currDbId
    };

    try {
        await rds.startDBInstance(params).promise();

        let instanceStatus = "";
        do {
            const data = await rds.describeDBInstances({ DBInstanceIdentifier: dbId }).promise();
            instanceStatus = data.DBInstances[0].DBInstanceStatus;
            if (instanceStatus !== "available") {
                await new Promise((resolve) => setTimeout(resolve, 15000)); 
            }
        } while (instanceStatus !== "available");

    } catch (error) {
        throw new Error('RDS instance failed to start', error);
    }
}

exports.stopRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        DBInstanceIdentifier: currDbId
    };

    try {
        await rds.stopDBInstance(params).promise();

        let instanceStatus = "";
        do {
            const data = await rds.describeDBInstances({ DBInstanceIdentifier: dbId }).promise();
            instanceStatus = data.DBInstances[0].DBInstanceStatus;
            if (instanceStatus !== "stopped") {
                await new Promise((resolve) => setTimeout(resolve, 15000)); 
            }
        } while (instanceStatus !== "stopped");

    } catch (error) {
        throw new Error('RDS instance failed to stop', error);
    }
}

exports.modifyRDSInstance = async (changedDbAttributes, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    await rds.modifyDBInstance(params).promise();
}

exports.rebootRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const data = await rds.rebootDBInstance({DBInstanceIdentifier: currDbId}).promise();

    return data;
}

exports.createRDSBackup = async (currDbId, snapshotId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        DBInstanceIdentifier: currDbId,
        DBSnapshotIdentifier: snapshotId
    };

    const data = await rds.createDBSnapshot(params).promise();

    return data;
}

exports.restoreRDSBackup = async (newCurrDbId, snapshotId, instanceClass, publiclyAccessible = false, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const params = {
        DBSnapshotIdentifier: snapshotId,
        DBInstanceIdentifier: newCurrDbId,
        DBInstanceClass: instanceClass, 
        PubliclyAccessible: publiclyAccessible
    };

    await rds.restoreDBInstanceFromDBSnapshot(params).promise();
}


exports.deleteRDSInstance = async (dbId, skipFinalSnapshot = true, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });
    
    const params = {
        DBInstanceIdentifier: dbId,
        SkipFinalSnapshot: skipFinalSnapshot 
    };

    if (!skipFinalSnapshot) {
        params.FinalDBSnapshotIdentifier = `final-snapshot-${Date.now()}`;
    }

    await rds.deleteDBInstance(params).promise();

    let isDeleted = false;
    while (!isDeleted) {
        try {
            await new Promise((resolve) => setTimeout(resolve, 15000)); 
            const data = await rds.describeDBInstances({ DBInstanceIdentifier: instanceId }).promise();
        } catch (error) {
            if (error.code === "DBInstanceNotFound") {
                isDeleted = true;
            } else {
                throw new Error(error.Message);
            }
        }
    }
}