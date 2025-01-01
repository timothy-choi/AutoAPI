const { Logging } = require('@google-cloud/logging');
const { PubSub } = require('@google-cloud/pubsub');
const { exec } = require('child_process');

exports.setUpPubSubTopic = async (projectId, topicName, serviceAccount) => {
    try {
        const pubSub = new PubSub({ projectId });

        const [topic] = await pubSub.createTopic(topicName);

        await topic.iam.setPolicy({
            bindings: [
              {
                role: 'roles/pubsub.publisher',
                members: [`serviceAccount:${serviceAccount}`],
              },
            ],
        });

        return topic;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createLoggingSink = async (projectId, topicName, sinkName) => {
    try {
        const logging = new Logging({ projectId: projectId });

        const sinkDestination = `pubsub.googleapis.com/projects/${projectId}/topics/${topicName}`;
   
        var [sink, logResponse] = await logging.sink(sinkName).create({
            destination: sinkDestination,
            filter: `logName="projects/${PROJECT_ID}/logs/${logName}"`, 
        });

        return logResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};
