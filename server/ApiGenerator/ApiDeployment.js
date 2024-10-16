const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const ApiDeployment = sequelize.define('ApiDeployment', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ProjectId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    Version: {
        type: String, 
        allowNull: false
    },
    BaseUrl: {
        type: String, 
        allowNull: false
    },
    CreatedAt: {
        type: Date,
        default: Date.now
    },
    UpdatedAt: {
        type: Date,
        default: Date.now
    }
});

module.exports = ApiDeployment;