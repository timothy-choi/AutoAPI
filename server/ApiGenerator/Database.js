const mongoose = require('mongoose');

const DatabaseSchema = new mongoose.Schema({
    Name: {
        type: String,
        required: true,
        unique: true
    },
    Description: {
        type: String
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
    }
});

const DatabaseDefinition = mongoose.model('DatabaseModel', DatabaseSchema);

module.exports = DatabaseDefinition;

