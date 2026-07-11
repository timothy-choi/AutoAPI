const amqp = require('amqplib');

exports.sendMessage = async (mqUrl, queueName, message) => {
    let connection;
    let channel;
    try {
        connection = await amqp.connect(mqUrl);
        channel = await connection.createChannel();

        await channel.assertQueue(queueName, { durable: true });

        channel.sendToQueue(queueName, Buffer.from(message), { persistent: true });
    } catch (error) {
        throw new Error(error.message);
    } finally {
        if (channel) await channel.close();
        if (connection) await connection.close();
    }
};

exports.consumeMessage = async (mqUrl, queueName) => {
    let connection;
    let channel;
    try {
        connection = await amqp.connect(mqUrl);
        channel = await connection.createChannel();

        await channel.assertQueue(queueName, { durable: true });

        return new Promise((resolve, reject) => {
            channel.consume(queueName, (msg) => {
                if (msg !== null) {
                    const messageContent = msg.content.toString();

                    channel.ack(msg);

                    resolve(messageContent);
                } else {
                    reject('No message received');
                }
            });
        });
    } catch (error) {
        throw new Error(error.message);
    } finally {
        if (channel) await channel.close();
        if (connection) await connection.close();
    }
};