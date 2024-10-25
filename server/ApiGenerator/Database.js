const mongoose = require('mongoose');

const DatabaseSchema = new mongoose.Schema({
    Name: {
        type: String,
        required: true,
        unique: true
    },
    Type: {
        type: String,
        required: true
    },
    Version: {
        type: String,
        required: true
    },
    Description: {
        type: String
    },
    DidUpdate: {
        type: Boolean
    },
    CreatedBy: {
        type: String,
        required: true
    },
    CreatedAt: {
        type: Date,
        default: Date.now
    },
    UpdatedAt: {
        type: Date,
        default: Date.now
    },
    UpdatedBy: {
        type: String
    },
    ModelsUsed: {
        type: [String],
        required: true
    },
    ModelTablesInfo: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    ModelDatabaseInstanceInfo: {
        type: mongoose.Schema.Types.Mixed,
        required: false
    },
    HealthStatus: {
        type: String,
        required: false
    },
    Status: {
        type: String,
        required: false
    },
    DatabaseChangeLog: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    },
    DatabaseOperationsLog: {
        type: [mongoose.Schema.Types.Mixed],
        required: false
    } 
});

const DatabaseDefinition = mongoose.model('DatabaseModel', DatabaseSchema);

module.exports = DatabaseDefinition;

