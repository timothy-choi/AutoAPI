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
    }
});

module.exports = UserStats;