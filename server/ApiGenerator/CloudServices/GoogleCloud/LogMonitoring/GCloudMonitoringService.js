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