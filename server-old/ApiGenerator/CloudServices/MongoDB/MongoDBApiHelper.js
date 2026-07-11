const axios = require("axios");

exports.GetApiClient = (apiKey) => {
    const apiClient = axios.create({
        baseURL: '',
        headers: {
          Authorization: `Bearer ${apiKey}`,
          "Content-Type": "application/json",
        },
      });

      return apiClient;
};