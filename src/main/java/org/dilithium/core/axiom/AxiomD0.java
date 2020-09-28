/* 
 * Copyright (C) 2018 Dilithium Team .
 *
 * The Dilithium library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

package org.dilithium.core.axiom;

import java.math.BigInteger;
import org.dilithium.Exceptions.AxiomUpdateException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.logging.Level;

import org.dilithium.config.NodeSettings;
import org.dilithium.core.AccountState;
import org.dilithium.core.Block;
import org.dilithium.core.BlockHeader;
import org.dilithium.core.Transaction;
import org.dilithium.core.genesis.GenesisBlock;
import org.dilithium.db.Context;
import org.dilithium.util.ByteUtil;
import org.dilithium.util.HashUtil;
import org.dilithium.util.Log;
import org.dilithium.util.ecdsa.ECKey;

import static org.dilithium.util.HashUtil.applyKeccak;

/**
 * AxiomD0 - Dilithium 0
 * This Proof of work axiom is for testing.
 * 
 * TODO: replace print with logger.
 */
public class AxiomD0 implements Axiom {

    @Override
    public byte[] getAxiomID() {
        return new byte[] { (byte) 0,(byte) 0 }; //the default axiom id.
    }

    @Override
    public boolean requireBlockUpdate() {
        return false;
    }

    @Override
    public boolean requireTransactonUpdate() {
        return false;
    }

    @Override
    public byte[] applyBlockHash(BlockHeader header) {
        byte[] data = header.getHashData();
        return HashUtil.applySha256(data);
    }

    @Override
    public long calculateDifficulty(BlockHeader header) {
        int difficulty = 2;
        return difficulty;
    }

    @Override
    public BlockHeader updateBlock(BlockHeader header) {
        //This shouldn't happen.
        throw new AxiomUpdateException();
    }

    @Override
    public Transaction updateTransction(Transaction tx) {
        //This shouldn't happen.
        throw new AxiomUpdateException();
    }
    
    @Override
    public BigInteger getBlockReward(BlockHeader headers){
        return BigInteger.valueOf(0L);
    }

    @Override
    public boolean verifyTransaction(Transaction tx, Context context) {
        //check if transaction signature is valid;
        if(!tx.isVerified()){
            if(!tx.verifySignature(this)) return false;
        }
        
        //Get Contextual AccountState context
        AccountState account = context.getAccount(tx.getSender());
        
        //check transaction nonce is greater than account nonce:
        if( !(ByteUtil.bytesToBigInteger(tx.getNonce()).compareTo(account.getNonce()) == 1)  ){ //fix this
            return false;
        }
        //check account has enough balance to make the transaction:
        if( (ByteUtil.bytesToBigInteger(tx.getValue()).compareTo(account.getBalance()) == 1 ) ){
            return false;
        }
        
        //check the network ID;
        if( tx.networkId() != NodeSettings.getDefault().getNetworkID() ){
            return false;
        }
        
        //Transaction is verified;
        return true;
    }

    @Override
    public boolean verifySignature(Transaction tx) {
        boolean verified = false;
        try {
            ECKey.ECDSASignature sig = ECKey.ECDSASignature.fromComponents(tx.getR(), tx.getS(), tx.getV());
            ECKey temp = ECKey.fromPublicOnly(ECKey.signatureToKeyBytes(applyKeccak(tx.getParcelledSansSig()), sig));
            if(ECKey.verifyWithRecovery(applyKeccak(tx.getParcelledSansSig()), sig) && Arrays.equals(tx.getSender(),temp.getAddress())) verified = true;
        } catch (Exception e) {
            verified = false;
        }
        return verified;
    }

    @Override
    public ECKey.ECDSASignature generateSignature(Transaction tx, byte[] privateKey) {
        return (ECKey.fromPrivate(privateKey)).sign(applyKeccak(tx.getParcelledSansSig()));
    }
    
    @Override
    public String toString(){
        return "AxiomD0 - Dilithium 0";
    }

    @Override
    public byte[] generateBlockSolution(BlockHeader header) {
        //increment nonce
        header.setNonce(ByteUtil.increment(header.getNonce()));
        //System.out.println("Hash Data: " + Hex.toHexString(header.getHashData()));
        //apply block hash
        return header.setBlockHash(applyBlockHash(header));
    }
    
    @Override
    public boolean isBlockSolutionValid(BlockHeader header) {
        byte[] solution = header.getHash();
        int difficulty = (int) header.getDifficulty();
        //get the blocks hash.
        byte[] target = new byte[(int) difficulty];
        target = ByteUtil.populate(target, (byte) 0);
        return Arrays.equals(Arrays.copyOfRange(solution, 0, target.length), target);
        
    }
    
    @Override
    public boolean isBlockValid(Block block, Context context){
        
        boolean isGenesis;
        
        //Get Block header and parent hash:
        BlockHeader header = block.header;
        Block parent = context.getBlock(header.getIndex()-1);
        BlockHeader parentHeader;
        
        //Check the header exists...
        if(header == null){
            //No header !
            return false;
        }else{
            //is this the genesis block ?
            isGenesis = (header.getIndex() == 0);
        }
        
        if(isGenesis){
            if(Arrays.equals(GenesisBlock.getInstance().getHash(),header.getHash())){
                Log.log(Level.INFO, "valid Genesis block hash");// This is genesis block.
                return true;
            }else{
                Log.log(Level.INFO, "Invalid Genesis block hash");//This genesis blocks hash is invalid.
                return false;
            }
        }
        
        //Does this blocks parent exist (,if required)?
        if(parent == null){
            Log.log(Level.INFO, "parent coulnt be found");
            //This blocks parent is missing.
            return false;
        }else{
            parentHeader = parent.header;
        }
        
        //Is Parents Index one less than this blocks index ?
        if(header.getIndex()-1 != parentHeader.getIndex()){
            Log.log(Level.INFO, "Blocks index is inccorect");
            //This blocks index is incorrect
            return false;
        }
        
        //Check if blockheaders difficulty is valid
        if(header.getDifficulty() < calculateDifficulty(header)){
            Log.log(Level.INFO, "Difficulty is too small");
            //Difficulty is too small
            return false;
        }
        
        //Check to see if block was correctly mined.
        if(!isBlockSolutionValid(header)){
            //This blocks solution is invalid. The block hasnt be correctly mined.
            Log.log(Level.INFO, "Solution isnt valid");
            return false;
        }
        
        //check timestamp isnt to large
        if( ByteUtil.bytesToInt(header.getTimeStamp()) > ByteUtil.bytesToInt(ByteUtil.getNowTimeStamp()) ){
            //Timestamp is too large
            Log.log(Level.INFO, "Timestamp too large");
            return false;
        }
        
        //TODO: Should check timestamp isnt too small
        
        //Check merkles root from block matches the merkle root in the header
        if( !Arrays.equals(block.getMerkleRoot(),header.getMerkleRoot()) ){
            //merkles root do not match.
            Log.log(Level.INFO, "merkle root doesnt match");
            return false;
        }
        
        Transaction currentTx;
        //Check if each contained transaction is valid.
        for(int i=0; i < block.getTransactionLength(); i++){
            currentTx = block.getTransaction(i);
            if(currentTx == null){
                // A transaction is missing, or couldn'g be decoded.
                Log.log(Level.INFO, "transaction is missing");
                return false;
            }
            if(! verifyTransaction(currentTx,context)){
                // transaction is not valid !
                Log.log(Level.INFO, "transaction isnt valid");
                return false;
            }            
        }
        
        //Check coinbase address:
        if(header.getReward().compareTo(getBlockReward(header)) == 1 ){
            //Block reward is to large.
            Log.log(Level.INFO, "reward is too large");
            return false;
        }
        
        //block is valid
        return true;
    }
    
}
