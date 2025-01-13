const axios = require('axios');

const retryOperation = async (operation, retries = 3, delay = 1000) => {
    let attempt = 0;
    while (attempt < retries) {
        try {
            return await operation();
        } catch (error) {
            if (attempt === retries - 1) {
                throw new Error(`Operation failed after ${retries} attempts: ${error.message}`);
            }
            attempt++;
            console.log(`Retrying operation... Attempt ${attempt + 1}`);
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }
};

const pollFunction = async (operationFn, checkStatusFn, maxAttempts = 10, delay = 5000) => {
    let attempt = 0;
    while (attempt < maxAttempts) {
        try {
            const status = await checkStatusFn();
            if (status === 'SUCCESS' || status === 'DONE') {
                return { success: true, message: 'Operation completed successfully' };
            } else if (status === 'FAILURE') {
                throw new Error('Operation failed');
            }

            console.log(`Polling attempt ${attempt + 1}: Status - ${status}`);
            attempt++;
            await new Promise(resolve => setTimeout(resolve, delay));
        } catch (error) {
            if (attempt === maxAttempts - 1) {
                throw new Error(`Polling failed after ${maxAttempts} attempts: ${error.message}`);
            }
            console.log(`Polling attempt ${attempt + 1} failed: ${error.message}`);
            attempt++;
        }
    }

    throw new Error(`Polling timed out after ${maxAttempts} attempts`);
};

exports.createApi = async (token, apiName, url, realApiName) => {
    try {
        const data = {
            apiId: apiName,
            displayName: realApiName,
        };
        
        const operation = async () => {
            const response = await axios.post(url, data, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            });

            return response.data;
        };

        const operationResponse = await retryOperation(operation, maxRetries, 2000);

        const checkStatusFn = async (operationName) => {
            const url = `https://apigateway.googleapis.com/v1/${operationName}`;
            const response = await axios.get(url, {
                headers: { Authorization: `Bearer ${token}` },
            });
    
            return response.data.done ? (response.data.error ? 'FAILURE' : 'SUCCESS') : 'PENDING';
        };

        const pollResult = await pollFunction(
            () => Promise.resolve(), 
            () => checkStatusFn(operationResponse.name), 
            pollAttempts,
            pollDelay
        );

        return pollResult;
    } catch (error) {
        throw new Error(error.message);
    }
};