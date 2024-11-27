const ServerlessFunction = require('./ApiServerlessFunction');

exports.GetServerlessFunctionById = async (serverlessFunctionId) => {
    var uuidVal = mongoose.types.ObjectId(serverlessFunctionId);

    var serverlessFunction = await ServerlessFunction.findById(uuidVal);

    return serverlessFunction;
}

exports.GetServerlessFunctionByFunctionName = async (functionName) => {
    var serverlessFunction = await ServerlessFunction.findOne({FunctionName: functionName});

    return serverlessFunction;
}

exports.CreateServerlessFunction = async (serverlessFunctionInfo) => {
    var serverlessFunction = await GetServerlessFunctionByFunctionName(serverlessFunctionInfo.functionName);

    if (serverlessFunction) {
        throw new Exception("function already exists");
    }

    serverlessFunction = await ServerlessFunction.Create({
        FunctionName: serverlessFunctionInfo.functionName,
        Runtime: serverlessFunctionInfo.runtime,
        Version: serverlessFunctionInfo.version,
        CreatedAt: Date.now(),
        Timeout: serverlessFunctionInfo.timeout
    });

    return serverlessFunction;
}

exports.SetFunctionVersion = async (serverlessFunctionId, versionValue) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.Version = versionValue;

    serverlessFunction.UpdatedAt = Date.now;

    await serverlessFunction.save();
}

exports.AddRoute = async (serverlessFunctionId, routeInfo) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.Routes.push(routeInfo);

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.RemoveRoute = async (serverlessFunctionId, serverlessFunctionRouteInfo) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.Routes.splice(serverlessFunction.Routes.indexOf(serverlessFunctionRouteInfo), 1);

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.EditRoute = async (serverlessFunctionId, serverlessFunctionRouteInfoId, updatedServerlessFunctionRouteInfo) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    var index = serverlessFunction.Routes.findIndex(serverlessFunctionRouteInfo = serverlessFunctionRouteInfo.id != serverlessFunctionRouteInfoId);

    serverlessFunction.Routes.splice(index, 1);

    serverlessFunction.Routes.splice(index, 0, updatedServerlessFunctionRouteInfo);

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.EditStatus = async (serverlessFunctionId, statusValue) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.Status = statusValue;

    await serverlessFunction.save();
}

exports.EditHealthStatus = async (serverlessFunctionId, healthStatusValue) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.HealthStatus = healthStatusValue;

    await serverlessFunction.save();
}

exports.EditTimeout = async (serverlessFunctionId, timeoutVal) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.Timeout = timeoutVal;

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.SetDeployedUrl = async (serverlessFunctionId, deployedUrl) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.DeployedUrl = deployedUrl;

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.ModifyEnvironmentVariables = async (serverlessFunctionId, envVariables) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.EnvironmentVariables = envVariables;

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.AddServerlessFunctionLog = async (serverlessFunctionId, functionLogEntry) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.Logs.push(functionLogEntry);

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.ModifyServerlessFunctionInfo = async (serverlessFunctionId, functionInfo) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.ServerlessFunctionInfo = functionInfo;

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.ModifyMetricsInfo = async (serverlessFunctionId, metricsInfo) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.MetricsInfo = metricsInfo;

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.ModifyErrorHandlingInfo = async (serverlessFunctionId, errorHandlingInfo) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.ErrorHandlingInfo = errorHandlingInfo;

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.AddServerlessFunctionVersionLog = async (serverlessFunctionId, functionVersionLogEntry) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.ServerlessFunctionVersionLog.push(functionVersionLogEntry);

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.ModifyIntegrationInfo = async (serverlessFunctionId, integrationInfo) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    serverlessFunction.IntegrationInfo = integrationInfo;

    serverlessFunction.UpdatedAt = Date.now();

    await serverlessFunction.save();
}

exports.DeleteServerlessFunction = async (serverlessFunctionId) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    await serverlessFunction.destroy();
}