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
    },
    AllApiModels: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: {}
    },
    AllApiEndpoints: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: {}
    },
    AllApiDatabases: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
        defaultValue: {}
    },
    ApiAuthentication: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    ApiDocumentation: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    ApiMonitoring: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    },
    ApiGateway: {
        type: DataTypes.JSONB,
        allowNull: true,
        defaultValue: {}
    }
});

module.exports = ApiDeployment;