const mongoose = require('mongoose');

const ApiTestingSchema = mongoose.Schema({
    ProjectId: {
        type: String,
        required: true,
        unique: true
    },
    AllModels: {
        type: [mongoose.Schema.Types.Mixed],
        required: true
    },
    AllEndpoints: {
        type: [mongoose.Schema.Types.Mixed],
        required: true
    },
});

const ApiTesting = mongoose.model('ApiTesting', ApiTestingSchema);

module.exports = ApiSecurityAuth;

