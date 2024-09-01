const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const UserStats = sequelize.define('UserStats', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    }, 
    UserId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    TotalNumberOfApisCreated: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0,
    },
    TotalNumberOfEndpointsCreated: {
        type: DataTypes.INTEGER,
        allowNull: false,
        defaultValue: 0,
    }
});

module.exports = UserStats;