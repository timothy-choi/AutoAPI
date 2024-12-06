const AWS = require('aws-sdk');

exports.createRDSInstance = async (dbInstanceInfo, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    const instanceData = await rds.createDBInstance(dbInstanceInfo).promise();

    let status = "creating";
    const MAX_WAIT_TIME = 30 * 60 * 1000; 
    let elapsedTime = 0;
    const POLLING_INTERVAL = 15000;

    while (status === "creating" && elapsedTime < MAX_WAIT_TIME) {
        await new Promise((resolve) => setTimeout(resolve, 15000));
        elapsedTime += POLLING_INTERVAL;

        const data = await rds.describeDBInstances({ DBInstanceIdentifier:  dbInstanceInfo.DBInstanceIdentifier}).promise();
        status = data.DBInstances[0].DBInstanceStatus;
    }

    if (elapsedTime >= MAX_WAIT_TIME) {
        throw new Error("Creating the instance is taking too long. Timeout exceeded.");
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

exports.checkRDSInstanceAvailability = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    try {
        const response = await rds
            .describeDBInstances({ DBInstanceIdentifier: dbInstanceIdentifier })
            .promise();

        const dbInstance = response.DBInstances[0];
        const status = dbInstance.DBInstanceStatus;

        return status === 'available'; 
    } catch (error) {
        if (error.code === 'DBInstanceNotFound') {
            return false;
        } else {
            throw new Error(`Error checking RDS instance status: ${error.message}`);
        }
    }
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
        const MAX_WAIT_TIME = 30 * 60 * 1000; // 30 minutes
        let elapsedTime = 0;
        const POLLING_INTERVAL = 15000;

        do {
            const data = await rds.describeDBInstances({ DBInstanceIdentifier: dbId }).promise();
            instanceStatus = data.DBInstances[0].DBInstanceStatus;
            if (instanceStatus !== "available") {
                await new Promise((resolve) => setTimeout(resolve, 15000));
                elapsedTime += POLLING_INTERVAL; 

                if (elapsedTime >= MAX_WAIT_TIME) {
                    throw new Error("Starting the instance is taking too long. Timeout exceeded.");
                }
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
        const MAX_WAIT_TIME = 30 * 60 * 1000; 
        const POLLING_INTERVAL = 15000; 
        let elapsedTime = 0;

        do {
            const data = await rds.describeDBInstances({ DBInstanceIdentifier: dbId }).promise();
            instanceStatus = data.DBInstances[0].DBInstanceStatus;
            if (instanceStatus !== "stopped") {
                await new Promise((resolve) => setTimeout(resolve, 15000));
                elapsedTime += POLLING_INTERVAL;
                
                if (elapsedTime >= MAX_WAIT_TIME) {
                    throw new Error("Stopping the instance is taking too long. Timeout exceeded.");
                }
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

    try {
        await rds.modifyDBInstance(changedDbAttributes).promise();

        await rds.waitFor("dBInstanceAvailable", { DBInstanceIdentifier: changedDbAttributes.DBInstanceIdentifier }).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.rebootRDSInstance = async (currDbId, userCredentials, userRegion) => {
    const rds = new AWS.RDS({
        credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
        region: userRegion
    });

    try {
        const rebootData = await rds.rebootDBInstance({DBInstanceIdentifier: currDbId}).promise();

        let isRebooting = true;
        const MAX_WAIT_TIME = 30 * 60 * 1000; // 30 minutes
        let elapsedTime = 0;
        const POLLING_INTERVAL = 15000;

        while (isRebooting && elapsedTime < MAX_WAIT_TIME) {
            await new Promise(res => setTimeout(res, 15000)); 
            elapsedTime += POLLING_INTERVAL;

            const data = await rds.describeDBInstances({ DBInstanceIdentifier: dbInstanceIdentifier }).promise();
            const status = data.DBInstances[0].DBInstanceStatus;
    
            if (status === 'available') {
                isRebooting = false;
            } 
        }

        if (elapsedTime >= MAX_WAIT_TIME) {
            throw new Error('Backup creation timed out');
        }
        return rebootData;
    } catch (error) {
        throw new Error(error.Message);
    }
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

    try {
        const snapshotData = await rds.createDBSnapshot(params).promise();

        let isCreating = true;
        const MAX_WAIT_TIME = 30 * 60 * 1000; 
        let elapsedTime = 0;
        const POLLING_INTERVAL = 15000;

        while (isCreating && elapsedTime < MAX_WAIT_TIME) {
            await new Promise(res => setTimeout(res, 15000));
            elapsedTime += POLLING_INTERVAL;

            const data = await rds.describeDBSnapshots({
                DBInstanceIdentifier: dbInstanceIdentifier,
                DBSnapshotIdentifier: snapshotName,
            }).promise();
            const status = data.DBSnapshots[0].Status;
    
            if (status === 'available') {
                isCreating = false;
            } 
        }

        if (elapsedTime >= MAX_WAIT_TIME) {
            throw new Error('Backup creation timed out');
        }
        return snapshotData;
    } catch (error) {
        throw new Error(error.Message);
    }
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

    try {
        await rds.restoreDBInstanceFromDBSnapshot(params).promise();

        let isRestoring = true;
        const MAX_WAIT_TIME = 30 * 60 * 1000; 
        let elapsedTime = 0;
        const POLLING_INTERVAL = 15000;

        while (isRestoring && elapsedTime < MAX_WAIT_TIME) {
            await new Promise(res => setTimeout(res, 15000));
            elapsedTime += POLLING_INTERVAL;

            const data = await rds.describeDBInstances({
                DBInstanceIdentifier: newDbInstanceIdentifier,
            }).promise();
            const status = data.DBInstances[0].DBInstanceStatus;
    
            if (status === 'available') {
                isRestoring = false;
            }
        }

        if (elapsedTime >= MAX_WAIT_TIME) {
            throw new Error('Backup creation timed out');
        }
    } catch (error) {
        throw new Error(error.Message);
    }
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
    const MAX_WAIT_TIME = 30 * 60 * 1000; // 30 minutes
    let elapsedTime = 0;
    const POLLING_INTERVAL = 15000;

    while (!isDeleted && elapsedTime < MAX_WAIT_TIME) {
        try {
            await new Promise((resolve) => setTimeout(resolve, 15000)); 
            elapsedTime += POLLING_INTERVAL;

            const data = await rds.describeDBInstances({ DBInstanceIdentifier: instanceId }).promise();
        } catch (error) {
            if (error.code === "DBInstanceNotFound") {
                isDeleted = true;
            } else {
                throw new Error(error.Message);
            }
        }
    }

    if (elapsedTime >= MAX_WAIT_TIME) {
        throw new Error('Backup creation timed out');
    }
}