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
    ModelAttributes: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: false,
    },
});

module.exports = Model;