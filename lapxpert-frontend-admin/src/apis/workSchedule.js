import {privateApi} from "./axiosAPI";

const API_URL = "/llv";

export default {
    getAllLLVs(){
        return privateApi.get(API_URL);
    },
};