const { Logging } = require('@google-cloud/logging');

exports.enableLogging = async (typeName, requestInfo, responseInfo) => {
    try {
        log = new Logging();

        const metadata = {
            resource: { type: typeName },
            labels: {
                requestInfo,
                responseInfo
            },
        };

        const entry = log.entry(metadata);

        await log.write(entry);
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createLoggingSink = async (destination, logName, sinkName) => {
    try {
        const logging = new Logging();

        const [sink] = await logging.createSink(sinkName, {
            destination: destination,
            filter: `logName=${logName}`,
        });
        
        return sink;
    } catch (error) {
        throw new Error(error.message);
    }
};