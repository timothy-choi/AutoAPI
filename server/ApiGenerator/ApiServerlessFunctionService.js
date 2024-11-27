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

exports.DeleteServerlessFunction = async (serverlessFunctionId) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    await serverlessFunction.destroy();
}