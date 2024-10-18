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
        type: DataTypes.STRING, 
        allowNull: false
    },
    BaseUrl: {
        type: DataTypes.STRING, 
        allowNull: false
    },
    CreatedAt: {
        type: DataTypes.DATE,
        defaultValue: Date.now
    },
    UpdatedAt: {
        type: DataTypes.DATE,
        defaultValue: Date.now
    },
    Status: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    Environment: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    DeployedAt: {
        type:  DataTypes.DATE,
        defaultValue: Date.now
    },
    DeployedDuration: {
        type: DataTypes.INTEGER,
        allowNull: true,
    }
});

module.exports = ApiDeployment;