const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Model = sequelize.define('Model', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ModelName: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    ModelCreatedAt: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: DataTypes.NOW,
    },
    ModelCreatedBy: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    ModelAttributes: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: false,
    },
    ModelUpdatedAt: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: DataTypes.NOW,
    },
    ModelUpdatedBy: {
        type: DataTypes.STRING,
        allowNull: true,
    },
    ModelDescription: {
        type: DataTypes.STRING,
        allowNull: false
    },
    ModelCreationFile: {
        type: DataTypes.JSONB,
        allowNull: true,
    },
    ModelDatabaseInfo: {
        type: DataTypes.JSONB,
        allowNull: true,
    },
});

module.exports = Model;