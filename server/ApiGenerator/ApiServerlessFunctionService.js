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

exports.DeleteServerlessFunction = async (serverlessFunctionId) => {
    var serverlessFunction = await GetServerlessFunctionById(serverlessFunctionId);

    if (!serverlessFunction) {
        throw new Exception("function does not exist");
    }

    await serverlessFunction.destroy();
}