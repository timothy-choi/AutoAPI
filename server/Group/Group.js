const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Group = sequelize.define('Group', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    GroupName: {
        type: DateTypes.STRING,
        allowNull: false,
        unique: true,
    },
    GroupUsers: {
        type: DateTypes.JSONB,
        allowNull: false,
    }, 
    CanJoin: {
        type: DateTypes.BOOLEAN,
        allowNull: false,
        default: true,
    },
    GroupProject: {
        type: DateTypes.JSONB,
        allowNull: false,
        defaultValue: {},
    },
});

module.exports = Group;